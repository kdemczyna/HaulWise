import { useParams } from "react-router";
import { PageHeader } from "../components/common/PageHeader.jsx";

export function PlanResultPage() {
  const { id } = useParams();
  return <PageHeader title="Plan result" description={`Plan ${id}`} />;
}
